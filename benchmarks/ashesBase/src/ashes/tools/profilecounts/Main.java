/* Soot - a J*va Optimization Framework
 * Copyright (C) 1997-1999 Raja Vallee-Rai
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/*
 * Modified by the Sable Research Group and others 1997-1999.  
 * See the 'credits' file distributed with Soot for the complete list of
 * contributors.  (Soot is distributed at http://www.sable.mcgill.ca/soot)
 */


package ashes.tools.profilecounts;

 
import soot.*;
import soot.jimple.*;
import soot.grimp.*;
import java.io.*;
import java.util.*;
import soot.util.*;

/**
    Invoke/cast count profiler.  Instruments the given application so that it counts the number of dynamic invokes that occur
    in the program.
 */
 
public class Main
{    
    public static void main(String[] args) 
    {
        if(args.length == 0)
        {
            System.out.println("Syntax: java ashes.tools.profilecounts.Main --app mainClass [soot options]");
            System.exit(0);
        }            
        
        Scene.v().getPack("wjtp").add(new Transform("wjtp.profiler", Profiler.v()));
        soot.Main.main(args);
    }
}


class Profiler extends SceneTransformer
{
    private static Profiler instance = new Profiler();
    private Profiler() {}
    static String oldPath;
    
    public static Profiler v() { return instance; }

    SootClass counterClass, systemClass;
    
    SootField interfaceInvokeCount,
        virtualInvokeCount,
        specialInvokeCount,
        staticInvokeCount,
        refCastCount;

    SootMethod systemExitMethod;
    
    void processClass(SootClass sClass)
    {
        // Add code to increase invoke counter each time an invokevirtual/interface is encountered
        {
            Iterator methodIt = sClass.getMethods().iterator();
            
            while(methodIt.hasNext())
            {
                SootMethod m = (SootMethod) methodIt.next();

                if(!m.isConcrete())
                    continue;
                
                JimpleBody body = (JimpleBody) m.retrieveActiveBody();
                                
                Local tmpLocal = Jimple.v().newLocal("tmp", LongType.v());
                body.getLocals().add(tmpLocal);

                Chain units = body.getUnits();
                Iterator stmtIt = units.snapshotIterator();
                                
                while(stmtIt.hasNext())
                {
                    Stmt s = (Stmt) stmtIt.next();

                    if(s.containsInvokeExpr())           
                    {
                        // Process this statement.
                        
                        InvokeExpr invokeExpr = (InvokeExpr) s.getInvokeExpr();                        

                        SootField counter = null;
                        
                        if(invokeExpr instanceof StaticInvokeExpr)
                            counter = staticInvokeCount;                            
                        else if(invokeExpr instanceof SpecialInvokeExpr)
                            counter = specialInvokeCount;                            
                        else if(invokeExpr instanceof VirtualInvokeExpr)
                            counter = virtualInvokeCount;                            
                        else if(invokeExpr instanceof InterfaceInvokeExpr)
                            counter = interfaceInvokeCount;                            

                        List l = new ArrayList();

                        l.add(Jimple.v().newAssignStmt(tmpLocal, 
                                Jimple.v().newStaticFieldRef(counter)));                        
                        l.add(Jimple.v().newAssignStmt(tmpLocal,
                                Jimple.v().newAddExpr(tmpLocal, LongConstant.v(1L))));
                                
                        l.add(Jimple.v().newAssignStmt(
                            Jimple.v().newStaticFieldRef(counter), tmpLocal));
                            
                        units.insertBefore(l, s);
                        
                        if(invokeExpr.getMethod() == systemExitMethod)
                        {   
                            // Add a call to CounterClass before the System.exit(x) call
                                units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(
                                    counterClass.getMethod("void stopProfiling()"))), s);
                        }                                                                                        
                    }
                    else if(s instanceof AssignStmt &&
                        ((AssignStmt) s).getRightOp() instanceof CastExpr)
                    {
                        CastExpr e = (CastExpr) ((AssignStmt) s).getRightOp();
                        
                        if(e.getCastType() instanceof RefType || e.getCastType() instanceof ArrayType)
                        {
                            List l = new ArrayList();
    
                            l.add(Jimple.v().newAssignStmt(tmpLocal, Jimple.v().newStaticFieldRef(refCastCount)));                        
                            l.add(Jimple.v().newAssignStmt(Jimple.v().newStaticFieldRef(refCastCount), 
                                    Jimple.v().newAddExpr(tmpLocal, LongConstant.v(1L))));
                            units.insertBefore(l, s);
                        }
                    }
                }
            }
        }
        
    }   
    
    protected void internalTransform(String phaseName, Map options)
    {        
        // Load counter counterClass
        {
            System.out.println("Locating CounterClass...");
            String oldPath = Scene.v().getSootClassPath();
            
            Scene.v().setSootClassPath("<external-class-path>");
            counterClass = Scene.v().loadClassAndSupport("ashes.tools.profilecounts.CounterClass");
            counterClass.setApplicationClass();        
            
            Scene.v().setSootClassPath(oldPath);
        }
                
        // Initialize some fields                        
            interfaceInvokeCount = counterClass.getField("long interfaceInvokeCount");
            virtualInvokeCount = counterClass.getField("long virtualInvokeCount");
            specialInvokeCount = counterClass.getField("long specialInvokeCount");
            staticInvokeCount = counterClass.getField("long staticInvokeCount");
            refCastCount = counterClass.getField("long refCastCount");

            systemClass = Scene.v().loadClassAndSupport("java.lang.System");
            systemExitMethod = systemClass.getMethod("void exit(int)");            

        // Handle each class
        {
            Iterator classIt = Scene.v().getApplicationClasses().iterator();
            
            while(classIt.hasNext())
            {
                SootClass sClass = (SootClass) classIt.next();
                
                if(sClass == counterClass)
                    continue;
                    
                System.out.print("Inserting counters for " + sClass.getName() + "... " );
                System.out.flush();
                       
                processClass(sClass);
                    
                System.out.println();
            }
        }

        // Add a call to CounterClass.stopProfiling() before each return in the main method.
        {
            SootClass sClass = Scene.v().getMainClass();
            SootMethod m = sClass.getMethod("void main(java.lang.String[])");
            JimpleBody body = (JimpleBody) m.getActiveBody();
            
            Chain units = body.getUnits();
            
            List l = new ArrayList(); l.addAll(units);

             Iterator stmtIt = l.listIterator();
            
            while(stmtIt.hasNext())
            {
                Stmt s = (Stmt) stmtIt.next();
                
                if(s instanceof ReturnVoidStmt)
                {
                    units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(
                        counterClass.getMethod("void stopProfiling()"))), s);       
                } 
            }
        }        

    }
}


